import { NextResponse } from 'next/server'
import { cookies } from 'next/headers'

const MS_EXECUTION_SERVICE_URL =
  process.env.EXECUTION_SERVICE_URL ??
  process.env.MS_EXECUTION_SERVICE_URL ??
  'http://localhost:8083'

export async function GET(
  request: Request,
  context: { params: Promise<{ id: string }> | { id: string } },
) {
  const cookieStore = await cookies()
  const resolved = await context.params
  const id = resolved.id

  try {
    const upstream = await fetch(
      `${MS_EXECUTION_SERVICE_URL}/api/execution/status/${encodeURIComponent(id)}`,
      {
        method: 'GET',
        headers: {
          cookie: cookieStore.toString(),
          accept: 'application/json',
        },
        cache: 'no-store',
      },
    )

    const data = await upstream.json().catch(() => null)

    const response = new NextResponse(JSON.stringify(data ?? {}), {
      status: upstream.status,
      headers: {
        'content-type': 'application/json',
        'cache-control': 'no-store',
      },
    })

    return response
  } catch (error) {
    return NextResponse.json(
      { error: 'Failed to fetch campaign status' },
      { status: 500 },
    )
  }
}

import { NextResponse } from 'next/server'
import { cookies } from 'next/headers'

const TEST_MANAGEMENT_SERVICE_URL =
  process.env.TEST_MANAGEMENT_SERVICE_URL ??
  process.env.PROJECTS_SERVICE_URL ??
  'http://localhost:8082'

function forwardSetCookie(upstream: Response, response: NextResponse) {
  const setCookies = (upstream.headers as any).getSetCookie?.() as string[] | undefined
  if (setCookies?.length) {
    for (const cookie of setCookies) response.headers.append('set-cookie', cookie)
    return
  }

  const maybeCookie = upstream.headers.get('set-cookie')
  if (maybeCookie) response.headers.append('set-cookie', maybeCookie)
}

export async function GET(
  request: Request,
  context: { params: Promise<{ id: string }> | { id: string } },
) {
  const cookieStore = await cookies()
  const url = new URL(request.url)
  const resolved = await context.params
  const id = resolved.id

  const upstream = await fetch(
    `${TEST_MANAGEMENT_SERVICE_URL}/api/executions/${encodeURIComponent(id)}${url.search}`,
    {
      method: 'GET',
      headers: {
        cookie: cookieStore.toString(),
        accept: request.headers.get('accept') ?? 'application/json',
      },
      cache: 'no-store',
    },
  )

  const response = new NextResponse(upstream.body, {
    status: upstream.status,
    headers: {
      'content-type': upstream.headers.get('content-type') ?? 'application/json',
      'cache-control': 'no-store',
    },
  })

  forwardSetCookie(upstream, response)
  return response
}

export async function PUT(
  request: Request,
  context: { params: Promise<{ id: string }> | { id: string } },
) {
  const cookieStore = await cookies()
  const url = new URL(request.url)
  const resolved = await context.params
  const id = resolved.id
  const body = await request.text().catch(() => '')

  const upstream = await fetch(
    `${TEST_MANAGEMENT_SERVICE_URL}/api/executions/${encodeURIComponent(id)}${url.search}`,
    {
      method: 'PUT',
      headers: {
        cookie: cookieStore.toString(),
        'content-type': request.headers.get('content-type') ?? 'application/json',
        accept: request.headers.get('accept') ?? 'application/json',
      },
      body,
    },
  )

  const response = new NextResponse(upstream.body, {
    status: upstream.status,
    headers: {
      'content-type': upstream.headers.get('content-type') ?? 'application/json',
    },
  })

  forwardSetCookie(upstream, response)
  return response
}

export async function DELETE(
  request: Request,
  context: { params: Promise<{ id: string }> | { id: string } },
) {
  const cookieStore = await cookies()
  const url = new URL(request.url)
  const resolved = await context.params
  const id = resolved.id

  const upstream = await fetch(
    `${TEST_MANAGEMENT_SERVICE_URL}/api/executions/${encodeURIComponent(id)}${url.search}`,
    {
      method: 'DELETE',
      headers: {
        cookie: cookieStore.toString(),
        accept: request.headers.get('accept') ?? 'application/json',
      },
    },
  )

  const response = new NextResponse(upstream.body, {
    status: upstream.status,
    headers: {
      'content-type': upstream.headers.get('content-type') ?? 'application/json',
    },
  })

  forwardSetCookie(upstream, response)
  return response
}

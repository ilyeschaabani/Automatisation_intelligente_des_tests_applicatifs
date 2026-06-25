import { NextResponse } from 'next/server'
import { cookies } from 'next/headers'

const MS_EXECUTION_SERVICE_URL =
  process.env.EXECUTION_SERVICE_URL ??
  process.env.MS_EXECUTION_SERVICE_URL ??
  'http://localhost:8083'

function buildHeaders(request: Request, cookieHeader: string) {
  const headers: Record<string, string> = {
    cookie: cookieHeader,
    accept: request.headers.get('accept') ?? 'application/json',
  }
  const auth = request.headers.get('authorization')
  if (auth) headers.authorization = auth
  return headers
}

export async function DELETE(
  request: Request,
  { params }: { params: Promise<{ id: string; userId: string }> },
) {
  const cookieStore = await cookies()
  const { id, userId } = await params

  const upstream = await fetch(
    `${MS_EXECUTION_SERVICE_URL}/api/functional-evaluation/${encodeURIComponent(id)}/members/${encodeURIComponent(userId)}`,
    {
      method: 'DELETE',
      headers: buildHeaders(request, cookieStore.toString()),
    },
  )

  return new NextResponse(upstream.body, {
    status: upstream.status,
    headers: { 'content-type': upstream.headers.get('content-type') ?? 'application/json' },
  })
}

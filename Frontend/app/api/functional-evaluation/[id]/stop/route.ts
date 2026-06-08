import { NextResponse } from 'next/server'
import { cookies } from 'next/headers'

const MS_EXECUTION_SERVICE_URL =
  process.env.EXECUTION_SERVICE_URL ??
  process.env.MS_EXECUTION_SERVICE_URL ??
  'http://localhost:8083'

export async function POST(
  request: Request,
  { params }: { params: Promise<{ id: string }> },
) {
  const cookieStore = await cookies()
  const { id } = await params

  if (!id || id === 'undefined') {
    return NextResponse.json({ error: 'Invalid evaluation id' }, { status: 400 })
  }

  const headers: Record<string, string> = {
    cookie: cookieStore.toString(),
  }
  const auth = request.headers.get('authorization')
  if (auth) headers.authorization = auth

  const upstream = await fetch(
    `${MS_EXECUTION_SERVICE_URL}/api/functional-evaluation/${encodeURIComponent(id)}/stop`,
    { method: 'POST', headers },
  )

  return new NextResponse(upstream.body, {
    status: upstream.status,
    headers: { 'content-type': 'application/json' },
  })
}

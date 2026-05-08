import { NextResponse } from 'next/server'
import { cookies } from 'next/headers'

const LLM_SERVICE_URL = process.env.LLM_SERVICE_URL ?? process.env.MS_EXECUTION_SERVICE_URL ?? 'http://localhost:8083'

export async function POST(request: Request) {
  const cookieStore = await cookies()
  const url = new URL(request.url)
  const body = await request.text().catch(() => '')

  const upstream = await fetch(`${LLM_SERVICE_URL}/api/llm/generate-test${url.search}`, {
    method: 'POST',
    headers: {
      'content-type': request.headers.get('content-type') ?? 'application/json',
    },
    body,
  })

  const response = new NextResponse(upstream.body, {
    status: upstream.status,
    headers: {
      'content-type': upstream.headers.get('content-type') ?? 'application/json',
    },
  })

  // forward set-cookie if present
  const setCookies = (upstream.headers as any).getSetCookie?.() as string[] | undefined
  if (setCookies?.length) {
    for (const cookie of setCookies) response.headers.append('set-cookie', cookie)
  } else {
    const maybe = upstream.headers.get('set-cookie')
    if (maybe) response.headers.append('set-cookie', maybe)
  }

  return response
}
